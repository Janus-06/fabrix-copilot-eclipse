package com.fabrix.copilot.core;

/**
 * 모델 정보 클래스
 */
public class ModelInfo {
    private final String modelId;
    private final String modelName;
    private final String modelLabel;
    private final String modelDescription;
    private final boolean isFabriX;
    
    public ModelInfo(String modelId, String modelName, String modelLabel, String modelDescription, boolean isFabriX) {
        this.modelId = modelId;
        this.modelName = modelName;
        this.modelLabel = modelLabel;
        this.modelDescription = modelDescription;
        this.isFabriX = isFabriX;
    }
    
    public String getModelId() { return modelId; }
    public String getModelName() { return modelName; }
    public String getModelLabel() { return modelLabel; }
    public String getModelDescription() { return modelDescription; }
    public boolean isFabriX() { return isFabriX; }
    
    @Override
    public String toString() {
        return modelLabel != null ? modelLabel : modelName;
    }
}