package aws.awsCopy;

import java.io.File;
import java.util.concurrent.TimeUnit;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 *  Copy a file to an S3 bucket.  For use when there is no aws client available on
 * the source server.
 * 
 * For list and description of parameters, invoke with the -h switch.
 * 
 * Author ijenkins
 * December 2020
 **/
public class CopyToS3 {
    private static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
    private static final String DEFAULT_PROXY_SERVER = "wsproxy.internal.ch";
    private static final String DEFAULT_PROXY_PORT = "8080";
    private static final String DEFAULT_ENCRYPTION_METHOD = "aws:kms";
    private static final String DEFAULT_KMS_KEY = "22d0b912-dac1-4cab-9622-7f6f68efab68";
    private static final String DEFAULT_SLEEP_INTERVAL = "10";
    private static Namespace ns = null;

    public static void main(String[] args) {
        
        ns = parseArguments(args);

        if (ns.getBoolean("verbose")) {
            System.out.println("Target bucket is" + ns.getString("bucket"));
            System.out.println("Source is " + ns.getString("source"));
            System.out.println("Destination is " + ns.getString("destination"));
            System.out.println("Proxy host is " + ns.getString("proxyHost"));
            System.out.println("Proxy port is " + ns.getString("proxyPort"));
            System.out.println("Sleep interval is " + ns.getString("sleepInterval"));
        }

        ClientConfiguration clientcfg = new ClientConfiguration();
        clientcfg.setProxyHost(ns.getString("proxyHost"));
        clientcfg.setProxyPort(Integer.parseInt(ns.getString("proxyPort")));

        TransferManager txManager = TransferManagerBuilder.standard().withS3Client(AmazonS3ClientBuilder.standard()
                .withClientConfiguration(clientcfg)
                .withCredentials(credentialsProvider).withRegion(Regions.EU_WEST_2).build()).build();

        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setHeader(Headers.SERVER_SIDE_ENCRYPTION, ns.getString("encryptionMethod"));
        objectMetadata.setHeader(Headers.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEYID, ns.getString("kmsKey"));
        PutObjectRequest putObjectRequest = new PutObjectRequest(ns.getString("bucket"), ns.getString("destination"), new File(ns.getString("source")));
        putObjectRequest.setMetadata(objectMetadata);
        Upload upload = txManager.upload(putObjectRequest);

        while (upload.isDone() == false) {
            System.out.println("Transfer: " + upload.getDescription());
            System.out.println("  - State: " + upload.getState());
            System.out.println("  - Progress: " + upload.getProgress().getBytesTransferred());
            try {
                TimeUnit.SECONDS.sleep(Integer.parseInt(ns.getString("sleepInterval")));
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
        System.out.println("Copy complete");
        txManager.shutdownNow();
    }
    
    private static Namespace parseArguments(String[] args) {
        
        ArgumentParser parser = ArgumentParsers.newFor("CopyToS3").build()
                .defaultHelp(true)
                .description("Upload file to s3 bucket");
        parser.addArgument("-b", "--bucket").required(true).help("The s3 bucket, eg: free.bulk-gateway.live.ch.gov.uk");
        parser.addArgument("-s", "--source").required(true).help("The (local) source file");
        parser.addArgument("-d", "--destination").required(true).help("The resource name on s3");
        parser.addArgument("-m", "--encryptionMethod").setDefault(DEFAULT_ENCRYPTION_METHOD).help("The kms encryption method");
        parser.addArgument("-k", "--kmsKey").setDefault(DEFAULT_KMS_KEY).help("The kms key");
        parser.addArgument("-x", "--proxyHost").setDefault(DEFAULT_PROXY_SERVER).help("The https proxy server");
        parser.addArgument("-p", "--proxyPort").setDefault(DEFAULT_PROXY_PORT).help("The https proxy port");
        parser.addArgument("-z", "--sleepInterval").setDefault(DEFAULT_SLEEP_INTERVAL).help("Interval between progress updates (in seconds)");
        parser.addArgument("-v", "--verbose").action(Arguments.storeTrue()).help("Turn on verbose mode");

        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        return ns;
    }
    
}
