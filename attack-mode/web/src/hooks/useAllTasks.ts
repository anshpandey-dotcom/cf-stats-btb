import { useQuery } from "@tanstack/react-query";

import { allTasks } from "../api/local";
import type { Task } from "../api/types";
import { isNative } from "../lib/native";

export function useAllTasks(): Task[] {
  const query = useQuery({
    queryKey: ["tasks", "all"],
    queryFn: async () => allTasks(),
    enabled: isNative(),
    staleTime: 0,
  });
  return query.data ?? [];
}
