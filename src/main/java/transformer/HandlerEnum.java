package transformer;

public enum HandlerEnum implements EnumMessage {
    INVOKE("Lorg/aspectj/lang/annotation/Around;", "@Around"),
    PRE_CODE("Lorg/aspectj/lang/annotation/Before;", "@Before"),
    COMPLETION("Lorg/aspectj/lang/annotation/AfterReturning;", "@AfterReturning"),
    EXCEPTION("Lorg/aspectj/lang/annotation/AfterThrowing;", "@AfterThrowing"),
    POST_CODE("Lorg/aspectj/lang/annotation/After;", "@After");


    private String annotationClassName;
    private String annotation;

    HandlerEnum(String annotationClassName, String annotation) {
        this.annotationClassName = annotationClassName;
        this.annotation = annotation;
    }

    public String getAnnotationClassName() {
        return annotationClassName;
    }

    public void setAnnotationClassName(String annotationClassName) {
        this.annotationClassName = annotationClassName;
    }

    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }


    @Override
    public Object getValue() {
        return annotationClassName;
    }
}
