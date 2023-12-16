package transformer;


public enum HandlerModelEnum implements EnumMessage {
    FILTER("FilterTransformer"),
    SHIRO("ShiroTransformer"),
    INTERCEPTOR("InterceptorTransformer"),
    AOP("AOPTransformer");


    private String framework;

    HandlerModelEnum(String frameworkName) {
        this.framework = frameworkName;
    }

    public String getFramework() {
        return framework;
    }

    public void setFramework(String framework) {
        this.framework = framework;
    }

    @Override
    public Object getValue() {
        return framework;
    }
}
