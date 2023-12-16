package bean;

import soot.SootClass;

public class XmlBeanClazz {
    private SootClass sootClass;
    private String scope;

    public XmlBeanClazz() {
    }

    public XmlBeanClazz(SootClass sootClass, String scope) {
        this.sootClass = sootClass;
        this.scope = scope;
    }

    @Override
    public String toString() {
        return "XmlBeanClazz{" +
                "sootClass=" + sootClass +
                ", scope='" + scope + '\'' +
                '}';
    }

    public SootClass getSootClass() {
        return sootClass;
    }

    public void setSootClass(SootClass sootClass) {
        this.sootClass = sootClass;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
