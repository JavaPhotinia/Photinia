import analysis.CreateEdge;
import ouptut.ForwardTransformer;
import soot.*;
import soot.options.Options;
import soot.shimple.Shimple;
import utils.BenchmarksConfig;
import utils.FileUtil;
import utils.GenJimpleUtil;

import java.io.File;
import java.util.*;

public class ParserSpringMain {
    public static void main(String[] args) {
        String benchmark = FileUtil.getConfigString("benchmark_name");
        initializeSoot(benchmark);
        CreateEdge createEdge = new CreateEdge();
        createEdge.initCallGraph();
        Scene.v().setMainClass(CreateEdge.projectMainMethod.getDeclaringClass());

        Iterator<SootClass> iterator = Scene.v().getApplicationClasses().snapshotIterator();
        while (iterator.hasNext()) {
            SootClass applicationClass = iterator.next();
            for (SootMethod method : applicationClass.getMethods()) {
                if (!(method.isAbstract() || method.isNative())) {
                    if (!method.hasActiveBody()) {
                        method.retrieveActiveBody();
                    }
                    Body body = method.getActiveBody();
                    //System.out.println(applicationClass.getName()+"----"+method.getName());
                    try {
                        body = Shimple.v().newBody(body);
                        method.setActiveBody(body);
                    } catch (Exception e) {
                        System.err.println(method);
                        e.printStackTrace();
                    }
                }

            }
            GenJimpleUtil.write(applicationClass);
        }

        Pack pack = PackManager.v().getPack("cg");
        pack.apply();

        pack = PackManager.v().getPack("wjtp");
        pack.add(new Transform("wjtp.ForwardTrans", new ForwardTransformer(BenchmarksConfig.getDependencyDir(benchmark))));
        pack.apply();
    }

    public static void initializeSoot(String benchmark) {
        G.reset();
        List<String> dir = BenchmarksConfig.getSourceProcessDir(benchmark);

        System.out.println(dir);
        Options.v().set_process_dir(dir);

        Options.v().setPhaseOption("cg.spark", "on");
        Options.v().setPhaseOption("cg.spark", "verbose:true");
        Options.v().setPhaseOption("cg.spark", "enabled:true");
        Options.v().setPhaseOption("cg.spark", "propagator:worklist");
        Options.v().setPhaseOption("cg.spark", "simple-edges-bidirectional:false");
        Options.v().setPhaseOption("cg.spark", "on-fly-cg:true");
        Options.v().setPhaseOption("cg.spark", "double-set-old:hybrid");
        Options.v().setPhaseOption("cg.spark", "double-set-new:hybrid");
        Options.v().setPhaseOption("cg.spark", "set-impl:double");
        // Options.v().setPhaseOption("cg.spark", "apponly:true");
        Options.v().setPhaseOption("cg.spark", "apponly:false");
        Options.v().setPhaseOption("cg.spark", "simple-edges-bidirectional:false");
        Options.v().set_verbose(true);
        // Scene.v().addBasicClass("javax.annotation.meta.TypeQualifier", HIERARCHY);
        // Scene.v().addBasicClass("javax.mail.internet.MimeMessage$RecipientType",HIERARCHY);
        // Scene.v().addBasicClass("javax.mail.internet.MimeBodyPart$MimePartDataHandler",HIERARCHY);
        List<String> includeList = new LinkedList<>();
        // includeList.add("java.lang.*");
        // includeList.add("java.util.*");
        // includeList.add("org.springframework.*");
        // includeList.add("org.springframework.security.*");
        // includeList.add("org.apache.shiro.spring.config.*");
        // includeList.add("com.alibaba.*");
        // includeList.add("com.baomidou.mybatisplus.*");
        // includeList.add("java.io.");
        // includeList.add("java.security.");
        // includeList.add("javax.servlet.");
        // includeList.add("javax.crypto.");
        Options.v().set_include(includeList);
        // Options.v().set_include_all(true);
        Options.v().set_whole_program(true);
        Options.v().set_src_prec(Options.src_prec_class);
        Options.v().set_ignore_classpath_errors(true);
        Options.v().set_exclude(excludedPackages());
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_no_bodies_for_excluded(true);

        Options.v().set_keep_line_number(true);
        String libClassPaths = getSootClassPath(benchmark);
        Options.v().set_soot_classpath(libClassPaths);
        Options.v().set_output_format(Options.output_format_jimple);
        FileUtil.preprocessLib(libClassPaths);
        Scene.v().loadNecessaryClasses();
        PhaseOptions.v().setPhaseOption("jb", "use-original-names:true");
    }

    private static String getSootClassPath(String benchmark) {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.equals(""))
            throw new RuntimeException("Could not get property java.home!");

        StringBuilder sootCp = new StringBuilder(javaHome + File.separator + "lib" + File.separator + "rt.jar");
        sootCp.append(File.pathSeparator).append(javaHome).append(File.separator).append("lib").append(File.separator).append("jce.jar");

        File file = new File(BenchmarksConfig.getDependencyDir(benchmark));
        File[] fs = file.listFiles();
        if (fs != null) {
            for (File f : Objects.requireNonNull(fs)) {
                if (!f.isDirectory())
                    sootCp.append(File.pathSeparator).append(BenchmarksConfig.getDependencyDir(benchmark)).append(File.separator).append(f.getName());
            }
        }
        return sootCp.toString();
    }

    private static List<String> excludedPackages() {
        List<String> excludedPackages = new ArrayList<>();
        excludedPackages.add("com.codahale.*");
        excludedPackages.add("com.zaxxer.*");
        excludedPackages.add("org.python.*");
        // excludedPackages.add("net.*");
        // excludedPackages.add("redis.*");
        // excludedPackages.add("com.googlecode.*");
        // excludedPackages.add("io.*");
        // excludedPackages.add("com.alipay.*");
        // excludedPackages.add("org.apache.ibatis.*");
        // excludedPackages.add("org.openxmlformats.*");
        // excludedPackages.add("com.aliyun.*");
        // excludedPackages.add("com.google.*");
        // excludedPackages.add("freemarker.*");
        // excludedPackages.add("jdk.*");
        // excludedPackages.add("kotlin.*");
        return excludedPackages;
    }
}
