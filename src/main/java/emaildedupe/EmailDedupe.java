package emaildedupe;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.S3Object;

/**
 * AWS Lambda routine that will read a specific file from S3, and upload
 * a specific file to S3 that contains the de-duplicated email list
 */

public class EmailDedupe {
    public void dedupe(Map<String, Object> input, Context context) throws IOException {
        AmazonS3 s3Client = new AmazonS3Client();
        // open emails.txt file from s3
        S3Object emailList = s3Client.getObject("email-dedupe-bucket", "emails.txt");
        InputStream stream = emailList.getObjectContent();
        BufferedReader buffReader = new BufferedReader(new InputStreamReader(stream));
        
        // create temporary file to write deduped list
        File output = File.createTempFile("output", "txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(output, true));
        
        // auxiliary data structures
        Map<String, Set<String>> storedEmails = new HashMap<String, Set<String>>();
        String currEmail;
        
        // read input by line and write out email only the first time they appear
        while ((currEmail = buffReader.readLine()) != null) {
            String[] splitEmail = currEmail.split("@", 2);
            Set<String> names = storedEmails.get(splitEmail[1]);
            
            if (names == null) {
                names = new HashSet<String>();
                storedEmails.put(splitEmail[1], names);
            }
            
            if (!names.contains(splitEmail[0])) {
                names.add(splitEmail[0]);
                writer.write(currEmail, 0, currEmail.length());
                writer.newLine();
            }
        }

        writer.flush();
        
        s3Client.putObject("email-dedupe-bucket", "output.txt", output);
        s3Client.setObjectAcl("email-dedupe-bucket", "output.txt", CannedAccessControlList.PublicRead);

        writer.close();
        buffReader.close();
        stream.close();
    }
}
