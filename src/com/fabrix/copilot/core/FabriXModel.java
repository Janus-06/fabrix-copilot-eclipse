package com.fabrix.copilot.core;

/**
 * FabriX 모델 정보
 */
public class FabriXModel {
    private final String modelId;
    private final String modelName;
    private final String modelLabel;
    private final String modelDescription;
    
    public FabriXModel(String modelId, String modelName, String modelLabel, String modelDescription) {
        this.modelId = modelId;
        this.modelName = modelName;
        this.modelLabel = modelLabel;
        this.modelDescription = modelDescription;
    }
    
    public String getModelId() { return modelId; }
    public String getModelName() { return modelName; }
    public String getModelLabel() { return modelLabel; }
    public String getModelDescription() { return modelDescription; }
    
    @Override
    public String toString() {
        return modelLabel != null ? modelLabel : modelName;
    }
}