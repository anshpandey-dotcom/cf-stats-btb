import type { ReactNode } from "react";
import { X } from "lucide-react";
import { Drawer } from "vaul";

interface SheetProps {
  open: boolean;
  title: string;
  subtitle?: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}

export function Sheet({ open, title, subtitle, onClose, children, footer }: SheetProps) {
  return (
    <Drawer.Root
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <Drawer.Portal>
        <Drawer.Overlay className="sheet-backdrop" />
        <Drawer.Content className="sheet" aria-describedby={undefined}>
          <div className="sheet-handle" aria-hidden="true" />
          <div className="sheet-header">
            <div>
              <Drawer.Title className="sheet-title">{title}</Drawer.Title>
              {subtitle ? <p className="sheet-subtitle">{subtitle}</p> : null}
            </div>
            <button type="button" className="icon-button sheet-close" onClick={onClose} aria-label="Close">
              <X size={20} />
            </button>
          </div>
          <div className="sheet-body" data-vaul-no-drag>
            {children}
          </div>
          {footer ? <div className="sheet-footer">{footer}</div> : null}
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}
