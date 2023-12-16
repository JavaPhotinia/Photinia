package transformer;

import bean.GotoEdge;
import soot.*;
import soot.jimple.JimpleBody;

import java.util.ArrayList;
import java.util.List;

public class InterceptorTransformer extends BasicCoRTransformer {
    @Override
    void addExtMockLocal(JimpleBody body) {

    }

    @Override
    public String getAnalysisName() {
        return "InterceptorTransformer";
    }

    @Override
    String getProxyClassName(SootClass targetClass) {
        return targetClass.getName()+"$$InterceptorProxy";
    }

    @Override
    public List<Type> additionalParamForMockProxy() {
        List<Type> typeList = new ArrayList<>();
        typeList.add(RefType.v("javax.servlet.http.HttpSession"));
        typeList.add(RefType.v("javax.servlet.http.HttpServletRequest"));
        typeList.add(RefType.v("javax.servlet.http.HttpServletResponse"));
        return typeList;
    }

    @Override
    public GotoEdge gotoLink() {
        return new GotoEdge(HandlerEnum.PRE_CODE, HandlerEnum.POST_CODE);
    }

    @Override
    public SootMethod getPreMethod() {
        return null;
    }

    @Override
    public boolean addFormalParamForAround() {
        return false;
    }

    @Override
    public boolean addSelfClassFormalParamForAround() {
        return false;
    }

    @Override
    public boolean specialPoint(Unit unit) {
        return false;
    }
}
