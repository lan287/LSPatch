package org.lsposed.lspd.service;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Binder;
import android.os.Parcel;
public interface IRemotePreferenceCallback extends IInterface {
    void onPreferenceChanged(String pref, String key, Object value) throws android.os.RemoteException;
    abstract class Stub extends Binder implements IRemotePreferenceCallback {
        private static final String DESCRIPTOR = "org.lsposed.lspd.service.IRemotePreferenceCallback";
        public Stub() { attachInterface(this, DESCRIPTOR); }
        public static IRemotePreferenceCallback asInterface(IBinder obj) { return null; }
        @Override public IBinder asBinder() { return this; }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) { return false; }
    }
}
