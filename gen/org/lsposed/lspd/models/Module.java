package org.lsposed.lspd.models;
import android.content.pm.ApplicationInfo;
import android.os.Parcel;
import android.os.Parcelable;
import org.lsposed.lspd.service.ILSPInjectedModuleService;
public class Module implements Parcelable {
    public String packageName;
    public int appId;
    public String apkPath;
    public PreLoadedApk file;
    public ApplicationInfo applicationInfo;
    public ILSPInjectedModuleService service;
    public Module() {}
    protected Module(Parcel in) {}
    public static final Creator<Module> CREATOR = new Creator<Module>() {
        @Override public Module createFromParcel(Parcel in) { return new Module(in); }
        @Override public Module[] newArray(int size) { return new Module[size]; }
    };
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {}
}
