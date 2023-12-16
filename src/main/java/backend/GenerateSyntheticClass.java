package backend;

import bean.ConstructorArgBean;
import bean.DBMethodBean;
import soot.SootClass;
import soot.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GenerateSyntheticClass {
    SootClass generateJoinPointImpl(SootClass abstractClass);

    SootClass generateMapperImpl(SootClass interfaceClass, DBMethodBean dbMethodBean);

    SootClass generateProxy(SootClass sootClass, String proxyClassName, List<Type> additionalParam);

    void generateSingletonBeanFactory(Set<SootClass> beans, Set<SootClass> singleBeans, Map<String, List<ConstructorArgBean>> collect);
    void generateDataTableClass(String tableName, DBMethodBean dbMethodBean);

    SootClass generateHttpServlet(SootClass abstractClass);

    SootClass generateHttpSession(SootClass abstractClass);

    SootClass generateFilterChain(SootClass abstractClass);

}
