package com.example.ragdemo.dto;

import java.util.List;

public class MallImageBatchRequest {

    private List<MallImageDocumentRequest> images;

    public List<MallImageDocumentRequest> getImages() {
        return images;
    }

    public void setImages(List<MallImageDocumentRequest> images) {
        this.images = images;
    }
}
