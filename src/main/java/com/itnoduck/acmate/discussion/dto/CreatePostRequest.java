package com.itnoduck.acmate.discussion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreatePostRequest {
    @NotBlank @Size(max = 255)
    private String title;
    @NotBlank
    private String contentMd;
    @NotBlank
    private String postType;
    private Long problemId;
    private Long trainingPlanId;
    private Boolean broadcast;
    private Boolean pinned;
    private Integer pinDurationHours;

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getContentMd() { return contentMd; }
    public void setContentMd(String v) { this.contentMd = v; }
    public String getPostType() { return postType; }
    public void setPostType(String v) { this.postType = v; }
    public Long getProblemId() { return problemId; }
    public void setProblemId(Long v) { this.problemId = v; }
    public Long getTrainingPlanId() { return trainingPlanId; }
    public void setTrainingPlanId(Long v) { this.trainingPlanId = v; }
    public Boolean getBroadcast() { return broadcast; }
    public void setBroadcast(Boolean v) { this.broadcast = v; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean v) { this.pinned = v; }
    public Integer getPinDurationHours() { return pinDurationHours; }
    public void setPinDurationHours(Integer v) { this.pinDurationHours = v; }
}
