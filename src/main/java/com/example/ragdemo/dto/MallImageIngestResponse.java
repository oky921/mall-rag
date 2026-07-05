package com.example.ragdemo.dto;

public class MallImageIngestResponse {

    private final boolean success;

    private final int images;

    private MallImageIngestResponse(boolean success, int images) {
        this.success = success;
        this.images = images;
    }

    public static MallImageIngestResponse ok(int images) {
        return new MallImageIngestResponse(true, images);
    }

    public boolean isSuccess() {
        return success;
    }

    public int getImages() {
        return images;
    }
}
