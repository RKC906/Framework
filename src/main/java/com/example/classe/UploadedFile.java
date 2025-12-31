package com.example.classe;

import java.io.*;
import java.nio.file.*;

public class UploadedFile {
    private String fileName;
    private String contentType;
    private long size;
    private InputStream inputStream;
    private File tempFile;

    public UploadedFile(String fileName, String contentType,
            long size, InputStream inputStream) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.inputStream = inputStream;
    }

    // Crée un File temporaire (sur demande)
    public File getAsFile() throws IOException {
        if (tempFile == null && inputStream != null) {
            tempFile = Files.createTempFile("upload_", "_" + fileName).toFile();
            tempFile.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }
        return tempFile;
    }

    // Sauvegarder
    public void saveTo(String destinationPath) throws IOException {
        File destFile = new File(destinationPath);
        Files.copy(getInputStream(), destFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    // Sauvegarder vers un File
    public void saveTo(File destinationFile) throws IOException {
        Files.copy(getInputStream(), destinationFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    // Lire en bytes
    public byte[] getBytes() throws IOException {
        if (inputStream != null) {
            return inputStream.readAllBytes();
        }
        return new byte[0];
    }

    // Getters
    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public InputStream getInputStream() {
        if (inputStream != null) {
            try {
                inputStream.reset();
            } catch (Exception e) {
                // Reset non supporté, on retourne le stream tel quel
            }
        }
        return inputStream;
    }

    // Nettoyage
    public void cleanup() {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                // Ignorer
            }
        }
    }

    @Override
    public String toString() {
        return "UploadedFile{fileName='" + fileName + "', size=" + size + "}";
    }
}