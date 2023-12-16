package bean;

import transformer.HandlerEnum;

public class GotoEdge {
    HandlerEnum sourceAdvice;
    HandlerEnum sinkAdvice;

    GotoStmtModel SourceModel;
    GotoStmtModel SinkModel;

    public GotoEdge(HandlerEnum sourceAdvice, HandlerEnum sinkAdvice) {
        this.sourceAdvice = sourceAdvice;
        this.sinkAdvice = sinkAdvice;
    }

    public GotoEdge() {
    }

    public HandlerEnum getSourceAdvice() {
        return sourceAdvice;
    }

    public void setSourceAdvice(HandlerEnum sourceAdvice) {
        this.sourceAdvice = sourceAdvice;
    }

    public HandlerEnum getSinkAdvice() {
        return sinkAdvice;
    }

    public void setSinkAdvice(HandlerEnum sinkAdvice) {
        this.sinkAdvice = sinkAdvice;
    }

    public GotoStmtModel getSourceModel() {
        return SourceModel;
    }

    public void setSourceModel(GotoStmtModel sourceModel) {
        SourceModel = sourceModel;
    }

    public GotoStmtModel getSinkModel() {
        return SinkModel;
    }

    public void setSinkModel(GotoStmtModel sinkModel) {
        SinkModel = sinkModel;
    }
}
