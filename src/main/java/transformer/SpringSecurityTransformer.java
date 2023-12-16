package transformer;

import bean.HandlerModel;
import soot.*;
import soot.jimple.JimpleBody;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;

import java.util.*;

public class SpringSecurityTransformer extends FilterTransformer {
    public Unit processSpringSecurity(Set<HandlerModel> configurationModels, JimpleBody CEBody, Unit invokeController) {
        Unit hockUnit = null;
        for (HandlerModel configurationModel : configurationModels) {
            Local roleRef = jimpleUtil.newLocalVar("role", RefType.v("java.lang.String"));
            Local existVirtualRef = jimpleUtil.checkLocalExist(CEBody, roleRef.getName());
            if (existVirtualRef != null) {
                roleRef = existVirtualRef;
            } else {
                CEBody.getLocals().add(roleRef);
                jimpleUtil.createAssignStmt(roleRef, StringConstant.v(""), CEBody.getUnits());
            }
            List<String> roleList = processAuthExpression(configurationModel.getRoleString());
            for (String roleString : roleList) {
                Local returnRef = jimpleUtil.addLocalVar("$stack" + roleString, BooleanType.v(), CEBody);
                Unit invokeEqualsUnit = invokeEquals(Scene.v().getSootClass("java.lang.String"), roleRef, returnRef, roleString);
                Unit ifStmt = jimpleUtil.createIfWithEq(returnRef, invokeController);
                CEBody.getUnits().add(invokeEqualsUnit);
                CEBody.getUnits().add(ifStmt);
                CEBody.getUnits().add(invokeController);
            }
        }
        return hockUnit;
    }

    private List<String> processAuthExpression(String authExpression) {
        String[] authExpressionParts = authExpression.split(" or ");
        List<String> roleList = new ArrayList<>();
        for (String authExpressionPart : authExpressionParts) {
            if (authExpressionPart.contains("hasRole") || authExpressionPart.contains("hasAuthority")) {
                String role = authExpressionPart.substring(authExpressionPart.indexOf("'") + 1,
                        authExpressionPart.lastIndexOf("'"));
                roleList.add(role);
            } else if (authExpressionPart.contains("hasAnyRole") || authExpressionPart.contains("hasAnyAuthority")) {
                String roleArray = authExpressionPart.substring(authExpressionPart.indexOf("(") + 1,
                        authExpressionPart.lastIndexOf(")")).replace("'", "").replace(" ", "");
                String[] roles = roleArray.split(",");
                roleList.addAll(Arrays.asList(roles));
            }
        }
        return roleList;
    }

    private Unit invokeEquals(SootClass stringClass, Local virtualRef, Local returnRef, String roleString) {
        SootMethod equalsMethod = stringClass.getMethodByName("equals");
        VirtualInvokeExpr invokeEquals
                = jimpleUtil.createVirtualInvokeExpr(virtualRef, equalsMethod, Collections.singletonList(StringConstant.v(roleString)));
        return jimpleUtil.createAssignStmt(returnRef, invokeEquals);
    }

    @Override
    public String getAnalysisName() {
        return "SpringSecurityTransformer";
    }

    @Override
    public boolean specialPoint(Unit unit) {
        return unit.toString().contains(doFilterMethodSig)
                && !unit.toString().contains("goto")
                && !unit.toString().contains("this.<");
    }
}
