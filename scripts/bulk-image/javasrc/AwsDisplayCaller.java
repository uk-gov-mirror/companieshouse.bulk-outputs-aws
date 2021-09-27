package aws.awsCopy;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;

public class AwsDisplayCaller {

    private static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
    
    public static void main(String[] args) {
        AWSSecurityTokenService stsService = AWSSecurityTokenServiceClientBuilder.standard()
                .withRegion(Regions.EU_WEST_2)
                .withCredentials(credentialsProvider)
                .build();

        GetCallerIdentityResult callerIdentity = stsService.getCallerIdentity(new GetCallerIdentityRequest());
        System.out.println("caller identity is " + callerIdentity.toString());

    }

}
