package utils;

import bean.HandlerTargetModel;
import soot.SootClass;
import soot.SootMethod;
import transformer.*;

public class FactoryUtil {
    private static final FilterTransformer filterTransformer = new FilterTransformer();
    private static final ShiroTransformer shiroTransformer = new ShiroTransformer();
    private static final InterceptorTransformer interceptorTransformer = new InterceptorTransformer();
    private static final AOPTransformer aopTransformer = new AOPTransformer();
    private static final SpringSecurityTransformer springSecurityTransformer = new SpringSecurityTransformer();

    public HandlerTargetModel getAopTargetInstance(SootClass sootClass, SootMethod sootMethod) {
        HandlerTargetModel atm = new HandlerTargetModel();
        atm.setSootClass(sootClass);
        atm.setClassName(sootClass.getName());
        atm.setSootMethod(sootMethod);
        atm.setMethodName(sootMethod.getSignature());
        return atm;
    }

    public static BasicCoRTransformer getCoRTransformer(HandlerModelEnum handlerModelEnum) {
        BasicCoRTransformer basicCoRTransformer;
        switch (handlerModelEnum) {
            case FILTER:
                basicCoRTransformer = filterTransformer;
                break;
            case SHIRO:
                basicCoRTransformer = shiroTransformer;
                break;
            case INTERCEPTOR:
                basicCoRTransformer = interceptorTransformer;
                break;
            case AOP:
                basicCoRTransformer = aopTransformer;
                break;
            default:
                basicCoRTransformer = springSecurityTransformer;
                break;
        }
        return basicCoRTransformer;
    }
}
