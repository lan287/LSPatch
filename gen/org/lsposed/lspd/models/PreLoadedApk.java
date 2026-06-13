package org.lsposed.lspd.models;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SharedMemory;
import java.util.List;
public class PreLoadedApk implements Parcelable {
    public List<SharedMemory> preLoadedDexes;
    public List<String> moduleClassNames;
    public List<String> moduleLibraryNames;
    public boolean legacy;
    public PreLoadedApk() {}
    protected PreLoadedApk(Parcel in) {}
    public static final Creator<PreLoadedApk> CREATOR = new Creator<PreLoadedApk>() {
        @Override public PreLoadedApk createFromParcel(Parcel in) { return new PreLoadedApk(in); }
        @Override public PreLoadedApk[] newArray(int size) { return new PreLoadedApk[size]; }
    };
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {}
}
