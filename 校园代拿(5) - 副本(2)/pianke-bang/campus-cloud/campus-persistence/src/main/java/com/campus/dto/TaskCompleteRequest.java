package com.campus.dto;

import java.util.List;

public class TaskCompleteRequest {
    private String note;
    private List<String> proofImages;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<String> getProofImages() {
        return proofImages;
    }

    public void setProofImages(List<String> proofImages) {
        this.proofImages = proofImages;
    }
}
