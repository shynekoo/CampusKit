// 强制易盾壳走 arm64 libnesec 加载路径（与真机一致），并记录 dlopen 失败原因。
Java.perform(function () {
    var MyJni = Java.use("com.netease.nis.wrapper.MyJni");
    MyJni.a.overload().implementation = function () {
        console.log("[hook] MyJni.a() -> false (force arm64 nesec path)");
        return false;
    };
    var Sys = Java.use("java.lang.System");
    Sys.loadLibrary.overload("java.lang.String").implementation = function (name) {
        try {
            console.log("[load] loadLibrary " + name);
            return this.loadLibrary(name);
        } catch (t) {
            console.log("[load] loadLibrary " + name + " FAILED: " + t);
            throw t;
        }
    };
    Sys.load.overload("java.lang.String").implementation = function (path) {
        try {
            console.log("[load] System.load " + path);
            return this.load(path);
        } catch (t) {
            console.log("[load] System.load " + path + " FAILED: " + t);
            throw t;
        }
    };
    console.log("[hook] installed");
});
