package bean;

import soot.SootClass;
import soot.SootMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class InterceptorBean {
    private SootClass interceptor;
    private final List<String> addPathPatterns = new ArrayList<>();
    private final List<String> excludePathPatterns = new ArrayList<>();
    private Collection<SootMethod> sootMethodList = new ArrayList<>();

    public InterceptorBean(SootClass interceptor) {
        this.interceptor = interceptor;
    }

    public SootClass getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(SootClass interceptor) {
        this.interceptor = interceptor;
    }

    public List<String> getAddPathPatterns() {
        return addPathPatterns;
    }

    public void addAddPathPatterns(String addPathPattern) {
        this.addPathPatterns.add(addPathPattern);
    }

    public List<String> getExcludePathPatterns() {
        return excludePathPatterns;
    }

    public void addExcludePathPatterns(String excludePathPattern) {
        this.excludePathPatterns.add(excludePathPattern);
    }

    public Collection<SootMethod> getSootMethodList() {
        return sootMethodList;
    }

    public void setSootMethodList(Collection<SootMethod> sootMethodList) {
        this.sootMethodList = sootMethodList;
    }
}
