import { Badge, Button } from 'antd';

export function MobileActionBar({ label, count, onClick }: { label: string; count?: number; onClick: () => void }) {
  return <div className="mobile-action-bar" role="toolbar">
    <Button type="primary" block onClick={onClick}>
      {label}{typeof count === 'number' && <Badge count={count} overflowCount={9999} style={{ marginLeft: 8, backgroundColor: '#fff', color: '#1677ff' }} />}
    </Button>
  </div>;
}
