import { backupStatus, saveFileToDocuments } from "./backup";
import { isNative } from "./native";

export async function deliverFile(
  folder: string,
  name: string,
  bytes: Uint8Array,
  mime: string,
): Promise<string> {
  if (isNative()) {
    const status = await backupStatus();
    if (!status.granted) {
      throw new Error("Allow file access first (Journal page) so files can be saved in Documents/Attack Mode.");
    }
    await saveFileToDocuments(folder, name, bytes);
    return `Documents/Attack Mode/${folder}`;
  }

  const url = URL.createObjectURL(new Blob([bytes as BlobPart], { type: mime }));
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return "Downloads";
}
