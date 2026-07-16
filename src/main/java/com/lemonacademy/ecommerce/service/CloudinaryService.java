package com.lemonacademy.ecommerce.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public String uploadImage(MultipartFile file) throws IOException {
        return uploadImage(file, null);
    }

    public String uploadImage(MultipartFile file, String folder) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        Map<String, Object> options = ObjectUtils.asMap("resource_type", "auto");
        if (folder != null && !folder.trim().isEmpty()) {
            options.put("folder", folder);
        }
        Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
        return uploadResult.get("secure_url").toString();
    }

    public List<String> uploadImages(List<MultipartFile> files) throws IOException {
        return uploadImages(files, null);
    }

    public List<String> uploadImages(List<MultipartFile> files, String folder) throws IOException {
        List<String> uploadedUrls = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return uploadedUrls;
        }

        try {
            for (MultipartFile file : files) {
                uploadedUrls.add(uploadImage(file, folder));
            }
        } catch (Exception e) {
            // Rollback already uploaded images
            deleteImages(uploadedUrls);
            throw new IOException("Failed to upload all images, rolling back. Error: " + e.getMessage(), e);
        }
        return uploadedUrls;
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        try {
            // Extract public ID from Cloudinary URL
            String publicId = extractPublicId(imageUrl);
            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            }
        } catch (Exception e) {
            // Log error but don't fail the transaction just for cleanup
            System.err.println("Failed to delete image from Cloudinary: " + imageUrl);
        }
    }

    public void deleteImages(List<String> imageUrls) {
        if (imageUrls == null) return;
        for (String url : imageUrls) {
            deleteImage(url);
        }
    }

    private String extractPublicId(String imageUrl) {
        try {
            // URL format typically: https://res.cloudinary.com/cloudName/image/upload/v1234567890/folder/filename.jpg
            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex == -1) return null;
            
            // Skip the version number if present (starts with 'v' followed by numbers)
            String path = imageUrl.substring(uploadIndex + 8);
            if (path.matches("^v\\d+/.*")) {
                path = path.substring(path.indexOf('/') + 1);
            }
            
            // Remove the extension
            int dotIndex = path.lastIndexOf('.');
            if (dotIndex != -1) {
                path = path.substring(0, dotIndex);
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }
}
