package org.jenkinsci.plugins.googleplayandroidpublisher.internal;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.codec.digest.DigestUtils;
import org.jenkinsci.plugins.googleplayandroidpublisher.Util.GetAppFileMetadataTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Locale;

public class UploadFile implements Serializable {

    private FilePath filePath;
    private AppFileMetadata metadata;
    private String sha1Hash;
    private FilePath mappingFile;
    private FilePath nativeDebugSymbolFile;

    public UploadFile(FilePath filePath) throws IOException, InterruptedException {
        this.filePath = filePath;
        this.metadata = filePath.act(new GetAppFileMetadataTask());
    }

    public FilePath getFilePath() {
        return filePath;
    }

    public AppFileFormat getFileFormat() {
        if (metadata instanceof ApkFileMetadata) {
            return AppFileFormat.APK;
        }
        if (metadata instanceof BundleFileMetadata) {
            return AppFileFormat.BUNDLE;
        }
        return AppFileFormat.UNKNOWN;
    }

    public String getApplicationId() {
        return metadata.getApplicationId();
    }

    public long getVersionCode() {
        return metadata.getVersionCode();
    }

    public String getVersionName() {
        return metadata.getVersionName();
    }

    public String getMinSdkVersion() {
        return metadata.getMinSdkVersion();
    }

    public AppFileMetadata getMetadata() {
        return metadata;
    }

    public String getSha1Hash() throws IOException, InterruptedException {
        if (sha1Hash == null) {
            sha1Hash = filePath.act(new GetHashTask());
        }
        return sha1Hash;
    }

    public FilePath getMappingFile() {
        return mappingFile;
    }

    public void setMappingFile(FilePath file) {
        this.mappingFile = file;
    }

    public FilePath getNativeDebugSymbolFile() {
        return nativeDebugSymbolFile;
    }

    public void setNativeDebugSymbolFile(FilePath file) {
        this.nativeDebugSymbolFile = file;
    }

    private static final class GetHashTask extends MasterToSlaveFileCallable<String> {
        @Override
        public String invoke(File file, VirtualChannel virtualChannel) throws IOException {
            try (FileInputStream fis = new FileInputStream(file)) {
                return DigestUtils.sha1Hex(fis).toLowerCase(Locale.ROOT);
            }
        }
    }

}
