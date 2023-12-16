package bean;

import soot.SootClass;
import soot.SootMethod;

public class ProxyModel {
    private SootClass proxyClass;
    private SootMethod proxyMethod;
    private String proxyClassName;
    private String proxyMethodName;
    private SootClass sootClass;
    private SootMethod sootMethod;

    public SootClass getProxyClass() {
        return proxyClass;
    }

    public void setProxyClass(SootClass proxyClass) {
        this.proxyClass = proxyClass;
    }

    public SootMethod getProxyMethod() {
        return proxyMethod;
    }

    public void setProxyMethod(SootMethod proxyMethod) {
        this.proxyMethod = proxyMethod;
    }

    public String getProxyClassName() {
        return proxyClassName;
    }

    public void setProxyClassName(String proxyClassName) {
        this.proxyClassName = proxyClassName;
    }

    public String getProxyMethodName() {
        return proxyMethodName;
    }

    public void setProxyMethodName(String proxyMethodName) {
        this.proxyMethodName = proxyMethodName;
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
}
