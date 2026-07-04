package com.mahak.courier.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {

    String uploadFile(MultipartFile file) throws IOException;

    void deleteFile(String objectKey) throws IOException;
}