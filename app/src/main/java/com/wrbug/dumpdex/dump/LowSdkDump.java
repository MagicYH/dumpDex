package com.wrbug.dumpdex.dump;

import android.app.Application;
import android.content.Context;

import com.wrbug.dumpdex.util.DeviceUtils;
import com.wrbug.dumpdex.util.FileUtils;
import com.wrbug.dumpdex.Native;
import com.wrbug.dumpdex.PackerInfo;
import com.wrbug.dumpdex.util.Helper;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LowSdkDump
 *
 * @author WrBug
 * @since 2018/3/23
 */
public class LowSdkDump {
    public static void log(String txt) {

        XposedBridge.log("dumpdex.LowSdkDump: " + txt);
        Helper.logToFile("dumpdex.LowSdkDump: " + txt);
    }

    public static void init(final XC_LoadPackage.LoadPackageParam lpparam, PackerInfo.Type type) {
        log("start hook Instrumentation#newApplication");
        if (DeviceUtils.supportNativeHook()) {
            log("Native dump");
            Native.dump(lpparam.packageName);
        }
        if (type == PackerInfo.Type.BAI_DU) {
            return;
        }

        log("LowSdkDump init");
        XposedHelpers.findAndHookMethod("com.stub.StubApp", lpparam.classLoader, "ᵢˋ", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                log("Hook into com.stub.StubApp");
                //获取到360的Context对象，通过这个对象来获取classloader
                Context context = (Context) param.args[0];

                if (context == null) {
                    log("classLoader is empty");
                    return;
                }

//                //获取360的classloader，之后hook加固后的代码就使用这个classloader
//                ClassLoader classLoader = context.getClassLoader();


                Application app = (Application) param.getResult();
                log("Before dump: " + app.toString());
                dump(lpparam.packageName, app.getClass());
                log("Finish dump");
                attachBaseContextHook(lpparam, app);
                log("Finish attach");
            }
        });

//        XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader, "newApplication", ClassLoader.class, String.class, Context.class, new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                log("Application=" + param.getResult());
//                dump(lpparam.packageName, param.getResult().getClass());
//                attachBaseContextHook(lpparam, ((Application) param.getResult()));
//            }
//        });
    }

    private static void dump(String packageName, Class<?> aClass) {
        Object dexCache = XposedHelpers.getObjectField(aClass, "dexCache");
        log("decCache=" + dexCache);
        Object o = XposedHelpers.callMethod(dexCache, "getDex");
        byte[] bytes = (byte[]) XposedHelpers.callMethod(o, "getBytes");
        String path = "/data/data/" + packageName + "/dump";
        File file = new File(path, "source-" + bytes.length + ".dex");

        log("Dump to path: " + path);
        if (file.exists()) {
            log(file.getName() + " exists");
            return;
        }
        FileUtils.writeByteToFile(bytes, file.getAbsolutePath());
    }


    private static void attachBaseContextHook(final XC_LoadPackage.LoadPackageParam lpparam, final Application application) {
        ClassLoader classLoader = application.getClassLoader();
        XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                log("loadClass->" + param.args[0]);
                Class result = (Class) param.getResult();
                if (result != null) {
                    dump(lpparam.packageName, result);
                }
            }
        });
        XposedHelpers.findAndHookMethod("java.lang.ClassLoader", classLoader, "loadClass", String.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                log("loadClassWithclassLoader->" + param.args[0]);
                Class result = (Class) param.getResult();
                if (result != null) {
                    dump(lpparam.packageName, result);
                }
            }
        });
    }
}
