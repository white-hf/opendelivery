package com.hf.easydelivery.delivery.controller;

import com.hf.easydelivery.common.dto.DeliveringListData;
import com.hf.easydelivery.common.response.AppResponse;
import com.hf.easydelivery.common.store.DeliveryOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import com.hf.easydelivery.common.exception.UnauthorizedException;
import com.hf.easydelivery.delivery.storage.PodStorage;

@RestController
@RequestMapping("/delivery")
public class DeliveryController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryController.class);
    private final DeliveryOperations dataStore;
    private final PodStorage podStorage;

    public DeliveryController(DeliveryOperations dataStore, PodStorage podStorage) {
        this.dataStore = dataStore;
        this.podStorage = podStorage;
    }

    @GetMapping("/parcels/tasks")
    public AppResponse<List<DeliveringListData>> getUnscannedList(
            @RequestParam("criteria") String criteria,
            @RequestParam("driver_id") Integer driverId,
            HttpServletRequest request) {
        requireSelf(driverId, request);
        log.info("Fetching unscanned task list for driverId={}, criteria={}", driverId, criteria);
        List<DeliveringListData> list = dataStore.getUnscannedParcels(driverId);
        return AppResponse.success(list);
    }

    @GetMapping("/parcels/delivering")
    public AppResponse<List<DeliveringListData>> getDeliveringList(
            @RequestParam("driver_id") Integer driverId,
            HttpServletRequest request) {
        requireSelf(driverId, request);
        log.info("Fetching delivering list for driverId={}", driverId);
        List<DeliveringListData> list = dataStore.getDeliveringParcels(driverId);
        return AppResponse.success(list);
    }

    @PostMapping(value = "", consumes = "multipart/form-data")
    public AppResponse<Void> uploadDeliveredPackages(
            @RequestParam("order_id") Long orderId,
            @RequestParam("longitude") Double longitude,
            @RequestParam("latitude") Double latitude,
            @RequestParam("delivery_result") Integer deliveryResult,
            @RequestParam(value = "failed_reason", required = false) Integer failedReason,
            @RequestParam(value = "recipient_name", required = false) String recipientName,
            @RequestParam(value = "idempotency_key", required = false) String idempotencyKey,
            @RequestParam(value = "pod_images[]", required = false) MultipartFile[] files,
            HttpServletRequest request) {
        
        int authenticatedDriverId = (Integer) request.getAttribute("driverId");

        if (deliveryResult == 0 && (files == null || files.length == 0)) {
            return AppResponse.fail("POD.EVIDENCE.REQUIRED", "POD photo evidence is required for successful delivery");
        }
        if (deliveryResult != 0 && failedReason == null) {
            return AppResponse.fail("PARAM.INVALID", "Failed reason is required for failed delivery attempts");
        }

        long attemptId = dataStore.recordDelivery(orderId, authenticatedDriverId, deliveryResult, failedReason, recipientName, latitude, longitude, idempotencyKey);

        if (files != null) {
            for (MultipartFile file : files) {
                log.info("Uploaded POD file name: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
                PodStorage.StoredPod stored = podStorage.store(file);
                if (attemptId > 0) dataStore.recordPod(attemptId, "PHOTO", stored.uri(), stored.sha256(), stored.contentType(), stored.size());
            }
        }

        return AppResponse.success("Upload successful", null);
    }

    @PostMapping(value = "/retry", consumes = "multipart/form-data")
    public AppResponse<Void> retryDelivery(
            @RequestParam("order_id") Long orderId,
            @RequestParam("longitude") Double longitude,
            @RequestParam("latitude") Double latitude,
            @RequestParam("driver_id") String driverId,
            @RequestParam(value = "pod_img[]", required = false) MultipartFile[] files,
            HttpServletRequest request) {

        int authenticatedDriverId = (Integer) request.getAttribute("driverId");
        if (!String.valueOf(authenticatedDriverId).equals(driverId)) {
            throw new UnauthorizedException("Driver cannot retry another driver's parcel");
        }

        log.info("Received delivery retry: orderId={}, driverId={}, lat={}, lng={}, filesCount={}",
                orderId, driverId, latitude, longitude, files != null ? files.length : 0);

        dataStore.retryDelivery(orderId, authenticatedDriverId);

        if (files != null) {
            for (MultipartFile file : files) {
                log.info("Uploaded POD retry file name: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
            }
        }

        return AppResponse.success("Retry recorded", null);
    }

    private void requireSelf(Integer requestedDriverId, HttpServletRequest request) {
        Object authenticated = request.getAttribute("driverId");
        Integer id = null;
        if (authenticated instanceof Integer intId) {
            id = intId;
        } else if (authenticated instanceof String strId) {
            try { id = Integer.parseInt(strId); } catch (Exception ignored) {}
        }
        if (id == null || !id.equals(requestedDriverId)) {
            throw new UnauthorizedException("Driver cannot access another driver's tasks");
        }
    }
}
