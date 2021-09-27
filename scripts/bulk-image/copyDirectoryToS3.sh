#export AWS_SHARED_CREDENTIALS_FILE=/export/home/new-bulk/image/bin/credentials
#echo credentials file is $AWS_SHARED_CREDENTIALS_FILE
arglist="$* -tupload"
#echo arglist is $arglist
java -jar s3CopyDir.jar $arglist

