package aws.awsCopy;

import java.io.File;
import java.util.concurrent.TimeUnit;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.ObjectMetadataProvider;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * Copy a directory to or from an S3 bucket. For use when there is no aws client
 * available on the source server.
 * 
 * For list and description of parameters, invoke with the -h switch.
 * 
 * Author ijenkins December 2020
 **/
public class S3CopyDir {
    private static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
    private static final String DEFAULT_PROXY_SERVER = "wsproxy.internal.ch";
    private static final String DEFAULT_PROXY_PORT = "8080";
    private static final String DEFAULT_ENCRYPTION_METHOD = "aws:kms";
    private static final String DEFAULT_KMS_KEY = "22d0b912-dac1-4cab-9622-7f6f68efab68";
    private static final String DEFAULT_SLEEP_INTERVAL = "10";
    private static Namespace namespace = null;
    private static String bucket = "";
    private static String directory = "";
    private static String awsVirtualDirectory = "";
    private static String proxyHost = "";
    private static String proxyPort = "";
    private static String encryptionMethod = "";
    private static String kmsKey = "";
    private static String sleepInterval = "";
    private static String transferDirection = "";
    private static boolean verbose = false;

    public static void main(String[] args) {

        parseArguments(args);

        if (verbose) {
            System.out.println("Bucket is " + bucket);
            System.out.println("Directory is " + directory);
            System.out.println("AWS virtual Directory is " + awsVirtualDirectory);
            System.out.println("Proxy host is " + proxyHost);
            System.out.println("Proxy port is " + proxyPort);
            System.out.println("Sleep interval is " + sleepInterval);
            System.out.println("Transfer direction is " + transferDirection);
        }

        ClientConfiguration clientcfg = new ClientConfiguration();
        clientcfg.setProxyHost(proxyHost);
        clientcfg.setProxyPort(Integer.parseInt(proxyPort));

        TransferManager txManager = TransferManagerBuilder.standard()
                .withS3Client(AmazonS3ClientBuilder.standard().withClientConfiguration(clientcfg)
                        .withCredentials(credentialsProvider).withRegion(Regions.EU_WEST_2).build())
                .build();

        if (transferDirection.equals("upload")) {
            uploadDirectory(txManager);
        } else {
            if (transferDirection.equals("download")) {
                downloadDirectory(txManager);
            }
        }

        txManager.shutdownNow();
    }

    private static void parseArguments(String[] args) {

        ArgumentParser parser = ArgumentParsers.newFor("S3Copy").build().defaultHelp(true);
        parser.addArgument("-b", "--bucket").required(true).help("The s3 bucket, eg: free.bulk-gateway.live.ch.gov.uk");
        parser.addArgument("-d", "--directory").required(true).help("The directory name on the local system.  Will also be appended to the awsVirtualDirectory when creating the resource in s3");
        parser.addArgument("-a", "--awsVirtualDirectory").required(true).help("The virtual directory path in the s3 bucket");
        parser.addArgument("-m", "--encryptionMethod").setDefault(DEFAULT_ENCRYPTION_METHOD)
                .help("The kms encryption method");
        parser.addArgument("-k", "--kmsKey").setDefault(DEFAULT_KMS_KEY).help("The kms key");
        parser.addArgument("-x", "--proxyHost").setDefault(DEFAULT_PROXY_SERVER).help("The https proxy server");
        parser.addArgument("-p", "--proxyPort").setDefault(DEFAULT_PROXY_PORT).help("The https proxy port");
        parser.addArgument("-z", "--sleepInterval").setDefault(DEFAULT_SLEEP_INTERVAL)
                .help("Interval between progress updates (in seconds)");
        parser.addArgument("-t", "--transferDirection").help(Arguments.SUPPRESS);
        parser.addArgument("-v", "--verbose").action(Arguments.storeTrue()).help("Turn on verbose mode");

        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        bucket = namespace.getString("bucket");
        directory = namespace.getString("directory");
        awsVirtualDirectory = namespace.getString("awsVirtualDirectory"); 
        encryptionMethod = namespace.getString("encryptionMethod");
        kmsKey = namespace.getString("kmsKey");
        proxyHost = namespace.getString("proxyHost");
        proxyPort = namespace.getString("proxyPort");
        sleepInterval = namespace.getString("sleepInterval");
        transferDirection = namespace.getString("transferDirection");
        verbose = namespace.getBoolean("verbose");
    }

    private static void uploadDirectory(TransferManager txMan) {
        System.out.println("directory is " + directory);

        File dir = new File(directory);
        System.out.println(dir.getAbsolutePath());

        ObjectMetadataProvider objectMetadataProvider = new ObjectMetadataProvider() {
            public void provideObjectMetadata(File file, ObjectMetadata metadata) {
                metadata.setHeader(Headers.SERVER_SIDE_ENCRYPTION, encryptionMethod);
                metadata.setHeader(Headers.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEYID, kmsKey);
            }
        };
        System.out.println(dir.getAbsolutePath());
        System.out.println("awsVirtualDirectory is " + awsVirtualDirectory);

        MultipleFileUpload directoryUpload = txMan.uploadDirectory(bucket, awsVirtualDirectory, dir, true, objectMetadataProvider);

        while (!directoryUpload.isDone()) {
            System.out.println("Transfer: " + directoryUpload.getDescription());
            System.out.println("  - State: " + directoryUpload.getState());
            System.out.println("  - Progress: " + directoryUpload.getProgress().getBytesTransferred());
            try {
                TimeUnit.SECONDS.sleep(Integer.parseInt(sleepInterval));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Block the current thread and wait for transfer to complete
        try {
            directoryUpload.waitForCompletion();
        } catch (InterruptedException e) {
            System.out.println("Transfer interrupted");
            e.printStackTrace();
        }
        System.out.println("Upload complete");
    }

    private static void downloadDirectory(TransferManager txMan) {
        System.out.println(directory);
        System.exit(0);
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, directory);
        Download directoryDownload = txMan.download(getObjectRequest, new File(directory));
        while (!directoryDownload.isDone()) {
            System.out.println("Transfer: " + directoryDownload.getDescription());
            System.out.println("  - State: " + directoryDownload.getState());
            System.out.println("  - Progress: " + directoryDownload.getProgress().getBytesTransferred());
            try {
                TimeUnit.SECONDS.sleep(Integer.parseInt(sleepInterval));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Block the current thread and wait for transfer to complete
        try {
            directoryDownload.waitForCompletion();
        } catch (InterruptedException e) {
            System.out.println("Transfer interrupted");
            e.printStackTrace();
        }
        System.out.println("Download complete");
    }

}
