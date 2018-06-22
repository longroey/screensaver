package com.didi.drivingrecorder.fota;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 更新包信息
 */
public class UpdateInfo implements Parcelable {

    /**
     * 是否有更新包
     */
    private boolean hasNew;

    /**
     * 当前系统版本名
     */
    private String currentVersionName;

    /**
     * 更新包系统版本名
     */
    private String newVersionName;

    /**
     * 更新包发布时间
     */
    private long newPublishTime;

    /**
     * 更新包大小
     */
    private long newSize;

    public UpdateInfo(){

    }

    protected UpdateInfo(Parcel in) {
        hasNew = in.readByte() != 0;
        currentVersionName = in.readString();
        newVersionName = in.readString();
        newPublishTime = in.readLong();
        newSize = in.readLong();
    }

    public boolean isHasNew() {
        return hasNew;
    }

    public void setHasNew(boolean hasNew) {
        this.hasNew = hasNew;
    }

    public String getCurrentVersionName() {
        return currentVersionName;
    }

    public void setCurrentVersionName(String currentVersionName) {
        this.currentVersionName = currentVersionName;
    }

    public String getNewVersionName() {
        return newVersionName;
    }

    public void setNewVersionName(String newVersionName) {
        this.newVersionName = newVersionName;
    }

    public long getNewPublishTime() {
        return newPublishTime;
    }

    public void setNewPublishTime(long newPublishTime) {
        this.newPublishTime = newPublishTime;
    }

    public long getNewSize() {
        return newSize;
    }

    public void setNewSize(long newSize) {
        this.newSize = newSize;
    }

    public static final Creator<UpdateInfo> CREATOR = new Creator<UpdateInfo>() {
        @Override
        public UpdateInfo createFromParcel(Parcel in) {
            return new UpdateInfo(in);
        }

        @Override
        public UpdateInfo[] newArray(int size) {
            return new UpdateInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (hasNew ? 1 : 0));
        dest.writeString(currentVersionName);
        dest.writeString(newVersionName);
        dest.writeLong(newPublishTime);
        dest.writeLong(newSize);
    }

    @Override
    public String toString() {
        return "UpdateInfo{" +
                "hasNew=" + hasNew +
                ", currentVersionName='" + currentVersionName + '\'' +
                ", newVersionName='" + newVersionName + '\'' +
                ", newPublishTime=" + newPublishTime +
                ", newSize=" + newSize +
                '}';
    }
}
