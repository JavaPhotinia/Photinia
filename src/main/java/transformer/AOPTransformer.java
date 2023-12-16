package transformer;

import backend.MockObject;
import backend.MockObjectImpl;
import bean.GotoEdge;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.JimpleBody;

import java.util.ArrayList;
import java.util.List;

import static utils.StringConstantUtil.AOP_PROCEED_METHOD_SIG;
import static utils.StringConstantUtil.AOP_PROCEED_METHOD_WITH_PAR_SIG;

public class AOPTransformer extends BasicCoRTransformer {
    @Override
    void addExtMockLocal(JimpleBody body) {
        MockObject mockObject = new MockObjectImpl();
        mockObject.mockJoinPoint(body, body.getUnits());
    }

    @Override
    public String getAnalysisName() {
        return "AOPTransformer";
    }

    @Override
    String getProxyClassName(SootClass targetClass) {
        return targetClass.getName() + "$$SpringCGLIB";
    }

    @Override
    public List<Type> additionalParamForMockProxy() {
        return new ArrayList<>();
    }

    @Override
    public GotoEdge gotoLink() {
        return null;
    }

    @Override
    public SootMethod getPreMethod() {
        return null;
    }

    @Override
    public boolean addFormalParamForAround() {
        return true;
    }

    @Override
    public boolean addSelfClassFormalParamForAround() {
        return true;
    }

    @Override
    public boolean specialPoint(Unit unit) {
        return (unit.toString().contains(AOP_PROCEED_METHOD_SIG)
                || unit.toString().contains(AOP_PROCEED_METHOD_WITH_PAR_SIG)) && !unit.toString().contains("goto");
    }
}
