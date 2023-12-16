package transformer;

import analysis.CreateEdge;
import bean.GotoEdge;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.JimpleBody;

import java.util.ArrayList;
import java.util.List;

public class FilterTransformer extends BasicCoRTransformer {
    protected String doFilterMethodSig = "<javax.servlet.FilterChain: void doFilter(javax.servlet.ServletRequest,javax.servlet.ServletResponse)>";

    @Override
    void addExtMockLocal(JimpleBody body) {}

    @Override
    public String getAnalysisName() {
        return "FilterTransformer";
    }

    @Override
    String getProxyClassName(SootClass targetClass) {
        return null;
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
        return CreateEdge.projectMainMethod;
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
        return unit.toString().contains(doFilterMethodSig) && !unit.toString().contains("goto");
    }
}
