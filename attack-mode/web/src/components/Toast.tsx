import { createContext, useContext, useMemo, type ReactNode } from "react";
import { toast, Toaster } from "sonner";

type ToastTone = "error" | "success" | "info";

interface ToastContextValue {
  showToast: (message: string, tone?: ToastTone) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const value = useMemo<ToastContextValue>(
    () => ({
      showToast(message, tone = "error") {
        if (tone === "success") toast.success(message);
        else if (tone === "info") toast(message);
        else toast.error(message);
      },
    }),
    [],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <Toaster theme="dark" position="top-center" duration={3000} visibleToasts={2} offset={12} />
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used inside ToastProvider");
  }
  return context;
}
