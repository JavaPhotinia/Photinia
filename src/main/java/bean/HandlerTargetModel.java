package bean;

import soot.SootClass;
import soot.SootMethod;
import transformer.HandlerModelEnum;

import java.util.*;

public class HandlerTargetModel {
    private String className;
    private String methodName;
    private SootClass sootClass;
    private SootMethod sootMethod;
    private List<HandlerModel> handlerChain = new ArrayList<>();
    private Set<SootMethod> pointcuts = new HashSet<>();
    private Map<String, ProxyModel> proxyModelMap = new HashMap<>();
    private Set<HandlerModelEnum> frameworkCategory = new HashSet<>();

    public HandlerTargetModel() {
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public SootClass getSootClass() {
        return sootClass;
    }

    public void setSootClass(SootClass sootClass) {
        this.sootClass = sootClass;
    }

    public SootMethod getSootMethod() {
        return sootMethod;
    }

    public void setSootMethod(SootMethod sootMethod) {
        this.sootMethod = sootMethod;
    }

    public List<HandlerModel> getHandlerChain() {
        return handlerChain;
    }

    public void setHandlerChain(List<HandlerModel> handlerChain) {
        this.handlerChain = handlerChain;
    }

    public void addHandlerIntoChain(HandlerModel handlerModel) {
        this.handlerChain.add(handlerModel);
    }

    public Set<SootMethod> getPointcuts() {
        return pointcuts;
    }

    public void setPointcuts(Set<SootMethod> pointcuts) {
        this.pointcuts = pointcuts;
    }

    public void addPoint(SootMethod method) {
        this.pointcuts.add(method);
    }

    public Set<HandlerModelEnum> getFrameworkCategory() {
        return frameworkCategory;
    }

    public void setFrameworkCategory(Set<HandlerModelEnum> frameworkCategory) {
        this.frameworkCategory = frameworkCategory;
    }

    public void addFrameworkCategory(HandlerModelEnum basicCoRAnalysis) {
        this.frameworkCategory.add(basicCoRAnalysis);
    }

    public Map<String, ProxyModel> getProxyModelMap() {
        return proxyModelMap;
    }

    public void setProxyModelMap(Map<String, ProxyModel> proxyModelMap) {
        this.proxyModelMap = proxyModelMap;
    }

    @Override
    public String toString() {
        return "HandlerTargetModel{" +
                "className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", sootClass=" + sootClass +
                ", sootMethod=" + sootMethod +
                ", advices=" + handlerChain +
                ", pointcuts=" + pointcuts +
                ", proxyModelMap=" + proxyModelMap +
                '}';
    }
}
