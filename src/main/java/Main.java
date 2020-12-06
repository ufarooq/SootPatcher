import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FileUtils;
import org.xml.sax.SAXException;
import soot.Body;
import soot.G;
import soot.PackManager;
import soot.PatchingChain;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.IntConstant;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.options.Options;

public class Main {

  // two args, config file and apk
  public static void main(String[] args)
      throws IOException, SAXException, ParserConfigurationException {
    // APK tool should be on path , all others will be loaded from config file
    Properties prop = new Properties();
    InputStream input = null;

    try {

      input = new FileInputStream(args[0]);
      // load a properties file
      prop.load(input);
      // get the property value and print it out
      System.out.println(prop.getProperty("classLocation"));
      System.out.println(prop.getProperty("outDirectory"));
      System.out.println(prop.getProperty("androidJar"));
      System.out.println(prop.getProperty("keyStore"));
      System.out.println(prop.getProperty("reports"));
      System.out.println(prop.getProperty("platformpk8"));
    } catch (IOException ex) {
      ex.printStackTrace();
      return;
    }

    String apkFile = args[1];
    String classLocation = prop.getProperty("classLocation").trim();
    String outDirectory = prop.getProperty("outDirectory").trim();
    String androidJar = prop.getProperty("androidJar").trim();
    String signapkJar = prop.getProperty("signapkJar").trim();
    String platformPem = prop.getProperty("platformPem").trim();
    String platformpk8 = prop.getProperty("platformpk8").trim();
    String zipalign = prop.getProperty("zipalign").trim();
    String keyStore = prop.getProperty("keyStore").trim();

    if (apkFile.endsWith(".apk")) {
      Path apk = new File(apkFile).toPath();
      String apkDirectory = apk.getParent().toAbsolutePath().toString();
      long startTime = Calendar.getInstance().getTimeInMillis();
      Options.v().set_src_prec(Options.src_prec_apk);

      ArrayList<String> list = new ArrayList<>();
      list.add(apkFile);
      list.add(classLocation);

      Options.v().set_process_dir(list);
      Options.v().set_src_prec(Options.src_prec_apk);
      Options.v().set_process_multiple_dex(true);
      Options.v().set_android_jars(androidJar);
      Options.v().set_output_dir(outDirectory);
      Options.v().set_allow_phantom_refs(true);
      Options.v().set_process_multiple_dex(true);
      Options.v().set_whole_program(true);
      Options.v().set_force_overwrite(true);
      Options.v().set_no_writeout_body_releasing(true);
      // Options.v().set_android_api_version(23);
      // output as APK, too//-f J
      Options.v().set_output_format(Options.output_format_dex);
      // Scene.v().loadNecessaryClasses();


      PackManager.v()
          .getPack("wjtp")
          .add(
              new Transform(
                  "wjtp.testPatcher",
                  new SceneTransformer() {
                    protected void internalTransform(String phaseName, Map options) {
                      // for Test
                      SootMethod onClick =
                          Scene.v()
                              .getMethod(
                                  "<com.example.umarfarooq.livedroidtest.MainActivity: void onClick1(android.view.View)>");
                      patchMethod(onClick);
                    }
                  }));


      String mainArgs =
          "-android-api-version 23"; // "-process-dir " + apk.toFile().getAbsolutePath();

      soot.Main.main(mainArgs.split("\\s")); //

      long timeSpentUntil = Calendar.getInstance().getTimeInMillis() - startTime;
      System.out.println("Completed Refactoring:" + timeSpentUntil);
      // PackManager.v().runPacks();
      PackManager.v().writeOutput();

      //
      System.out.println(
          "Completed Writing:" + (Calendar.getInstance().getTimeInMillis() - startTime));
      long time = Calendar.getInstance().getTimeInMillis();

      // String apkAlignedFileName = apk.getFileName().toString().replace(".", "_") + time +
      // "aligned.apk";
      String apkSignedFileName =
          apk.getFileName().toString().replace(".", "_") + time + "signed.apk";

      String alignCommand =
          zipalign
              + " -v -p 4 "
              + outDirectory
              + File.separator
              + apk.getFileName()
              + " "
              + outDirectory
              + File.separator
              + apkSignedFileName;
      String alignCommandOutput = executeCommand(alignCommand);
      System.out.println(alignCommandOutput);
      // String signCommand = "apksigner sign --ks " + keyStore + " --out " + apkSignedFileName + "
      // " + apkAlignedFileName;
      String signCommand =
          "apksigner sign --cert "
              + platformPem
              + " --key "
              + platformpk8
              + " "
              + outDirectory
              + File.separator
              + apkSignedFileName;
      String signCommandOutput = executeCommand(signCommand);
      System.out.println(signCommandOutput);
      long timeSpent = time - startTime;
      File resultFile = new File(apkDirectory + File.separator + "LiveDroid.csv");
      String appResult = apk.getFileName() + "," + timeSpent;
      try {
        // FileUtils.forceDelete(new File(outDirectory + File.separator + apkAlignedFileName));
        FileUtils.forceDelete(
            new File(outDirectory + File.separator + apk.getFileName().toString()));
        FileUtils.writeStringToFile(resultFile, appResult + "\n", Charset.defaultCharset(), true);
      } catch (IOException e) {
        e.printStackTrace();
      }
      G.reset();
      G.v().resetSpark();
    }
  }

  private static String executeCommand(String command) {

    StringBuffer output = new StringBuffer();

    Process p;
    try {
      p = Runtime.getRuntime().exec(command);
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

      String line = "";
      while ((line = reader.readLine()) != null) {
        output.append(line + "\n");
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

    return output.toString();
  }

  static void patchMethod(SootMethod sootMethod) {
    Body body = sootMethod.retrieveActiveBody();
    final PatchingChain<Unit> units = body.getUnits();
    Stmt firstNonIdentityStmt = ((JimpleBody) body).getFirstNonIdentityStmt();
    // System.exit(int);
    SootClass systemCls = Scene.v().getSootClass("java.lang.System");
    SootMethod exitMethod = systemCls.getMethodByNameUnsafe("exit");
    StaticInvokeExpr exitInvokeExpr =
        Jimple.v().newStaticInvokeExpr(exitMethod.makeRef(), IntConstant.v(1)); // exit(1)
    InvokeStmt exitInvokeStmt = Jimple.v().newInvokeStmt(exitInvokeExpr);
    units.insertAfter(exitInvokeStmt, firstNonIdentityStmt);
  }
}
