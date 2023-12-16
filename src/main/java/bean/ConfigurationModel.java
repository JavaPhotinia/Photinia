package bean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ConfigurationModel {
    private ArrayList<String> patterns;
    private String httpOrAuthMethod;
    private String authExpression;
    private final List<String> addPathPatterns = new ArrayList<>();
    private final List<String> excludePathPatterns = new ArrayList<>();

    public ConfigurationModel(String httpOrAuthMethod) {
        this.httpOrAuthMethod = httpOrAuthMethod;
        this.authExpression = "";
        this.patterns = new ArrayList<String>();
    }

    public ArrayList<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(ArrayList<String> patterns) {
        this.patterns = patterns;
    }

    public void addPattern(String pattern) {
        this.patterns.add(pattern);
    }

    public void addPatterns(Collection<String> patterns) {
        this.patterns.addAll(patterns);
    }

    public String getLastPattern() {
        return patterns.get(patterns.size() - 1);
    }

    public String getHttpOrAuthMethod() {
        return httpOrAuthMethod;
    }

    public void setHttpOrAuthMethod(String httpOrAuthMethod) {
        this.httpOrAuthMethod = httpOrAuthMethod;
    }

    public String getAuthExpression() {
        return authExpression;
    }

    public void setAuthExpression(String authExpression) {
        this.authExpression = authExpression;
    }

    public void appendAuthExpression(String appendExpression) {
        if(authExpression.equals("")) {
            this.authExpression += appendExpression;
        } else {
            this.authExpression += " and " + appendExpression;
        }
    }

    public void putAddPathPattern(String pattern) {
        addPathPatterns.add(pattern);
    }

    public List<String> getAddPathPatterns() {
        return addPathPatterns;
    }

    public void putExcludePathPattern(String pattern) {
        excludePathPatterns.add(pattern);
    }

    public List<String> getExcludePathPatterns() {
        return excludePathPatterns;
    }

    @Override
    public String toString() {
        return "Patterns: " + this.patterns + " with Method: " + this.httpOrAuthMethod
                + " authorization expression: " + this.authExpression;
    }
}
