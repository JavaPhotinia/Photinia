package bean;

import transformer.HandlerEnum;
import soot.Body;
import soot.Local;
import soot.Unit;

public class GotoStmtModel {
    Local returnLocal;
    Unit assignUnit;
    HandlerEnum handlerEnum;

    Body insertedBody;

    Unit gotoTmpUnit;

    public GotoStmtModel(Local returnLocal, Unit assignUnit) {
        this.returnLocal = returnLocal;
        this.assignUnit = assignUnit;
    }

    public GotoStmtModel() {
    }

    public Local getReturnLocal() {
        return returnLocal;
    }

    public void setReturnLocal(Local returnLocal) {
        this.returnLocal = returnLocal;
    }

    public Unit getAssignUnit() {
        return assignUnit;
    }

    public void setAssignUnit(Unit assignUnit) {
        this.assignUnit = assignUnit;
    }

    public HandlerEnum getAdviceEnum() {
        return handlerEnum;
    }

    public void setAdviceEnum(HandlerEnum handlerEnum) {
        this.handlerEnum = handlerEnum;
    }

    public Body getInsertedBody() {
        return insertedBody;
    }

    public void setInsertedBody(Body insertedBody) {
        this.insertedBody = insertedBody;
    }

    public Unit getGotoTmpUnit() {
        return gotoTmpUnit;
    }

    public void setGotoTmpUnit(Unit gotoTmpUnit) {
        this.gotoTmpUnit = gotoTmpUnit;
    }
}
