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
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * Copy a file to or from an S3 bucket. For use when there is no aws client available on
 * the source server.
 * 
 * For list and description of parameters, invoke with the -h switch.
 * 
 * Author ijenkins December 2020
 **/
public class S3CopyFile {
    private static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
    private static final String DEFAULT_PROXY_SERVER = "wsproxy.internal.ch";
    private static final String DEFAULT_PROXY_PORT = "8080";
    private static final String DEFAULT_ENCRYPTION_METHOD = "aws:kms";
    private static final String DEFAULT_KMS_KEY = "22d0b912-dac1-4cab-9622-7f6f68efab68";
    private static final String DEFAULT_SLEEP_INTERVAL = "10";
    private static Namespace namespace = null;
    private static String bucket = "";
    private static String localFile = "";
    private static String awsResource = "";
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
            System.out.println("Local file is " + localFile);
            System.out.println("aws resource is " + awsResource);
            System.out.println("Proxy host is " + proxyHost);
            System.out.println("Proxy port is " + proxyPort);
            System.out.println("Sleep interval is " + sleepInterval);
            System.out.println("Transfer direction is " + transferDirection);
        }

        ClientConfiguration clientcfg = new ClientConfiguration();
        clientcfg.setProxyHost(proxyHost);
        clientcfg.setProxyPort(Integer.parseInt(proxyPort));

        TransferManager txManager = TransferManagerBuilder.standard().withS3Client(AmazonS3ClientBuilder.standard()
                .withClientConfiguration(clientcfg)
                .withCredentials(credentialsProvider).withRegion(Regions.EU_WEST_2).build()).build();
        
        if (transferDirection.equals("upload")) {
            uploadFile(txManager);
        } else {
            if (transferDirection.equals("download")) {
                downloadFile(txManager);
            }
        }

        txManager.shutdownNow();
    }

    private static void parseArguments(String[] args) {

        ArgumentParser parser = ArgumentParsers.newFor("S3CopyFile").build().defaultHelp(true);
        parser.addArgument("-b", "--bucket").required(true).help("The s3 bucket, eg: free.bulk-gateway.live.ch.gov.uk");
        parser.addArgument("-a", "--awsResource").required(true).help("The resource name on s3");
        parser.addArgument("-l", "--localFile").required(true).help("The file on the local system");
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
        awsResource = namespace.getString("awsResource");
        localFile = namespace.getString("localFile");
        encryptionMethod = namespace.getString("encryptionMethod");
        kmsKey = namespace.getString("kmsKey");
        proxyHost = namespace.getString("proxyHost");
        proxyPort = namespace.getString("proxyPort");
        sleepInterval = namespace.getString("sleepInterval");
        transferDirection = namespace.getString("transferDirection");
        verbose = namespace.getBoolean("verbose");
        
/**        if (transferDirection.equals("upload")) {
            parser.description("Upload a file to an s3 bucket");
        } else {
            parser.description("Download a file from an s3 bucket");
        }  
**/            
    }

    private static void uploadFile(TransferManager txMan) {
        
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setHeader(Headers.SERVER_SIDE_ENCRYPTION, encryptionMethod);
        objectMetadata.setHeader(Headers.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEYID, kmsKey);
        
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, awsResource,
                new File(localFile));
        putObjectRequest.setMetadata(objectMetadata);
        
        Upload upload = txMan.upload(putObjectRequest);
        
        while (!upload.isDone()) {
            System.out.println("Transfer: " + upload.getDescription());
            System.out.println("  - State: " + upload.getState());
            System.out.println("  - Progress: " + upload.getProgress().getBytesTransferred());
            try {
                TimeUnit.SECONDS.sleep(Integer.parseInt(sleepInterval));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Block the current thread and wait for transfer to complete
        try {
            upload.waitForCompletion();
        } catch (InterruptedException e) {
            System.out.println("Transfer interrupted");
            e.printStackTrace();
        }
        System.out.println("Upload complete");
    }

    private static void downloadFile(TransferManager txMan) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, awsResource);
        Download download = txMan.download(getObjectRequest, new File(localFile));
        while (!download.isDone()) {
            System.out.println("Transfer: " + download.getDescription());
            System.out.println("  - State: " + download.getState());
            System.out.println("  - Progress: " + download.getProgress().getBytesTransferred());
            try {
                TimeUnit.SECONDS.sleep(Integer.parseInt(sleepInterval));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Block the current thread and wait for transfer to complete
        try {
            download.waitForCompletion();
        } catch (InterruptedException e) {
            System.out.println("Transfer interrupted");
            e.printStackTrace();
        }
        System.out.println("Download complete");
    }

}
