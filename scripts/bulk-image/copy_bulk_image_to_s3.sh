exec >> /export/home/dps/awscopy/copy_bulk_image_to_s3.log

echo "****************************************************************************"
echo "$0 begins at `date`"
echo "****************************************************************************"

valid_sub_products=" 287 288 363 acc cap cnm indexes liq misc mort newc rec_dates rec_ext scud "

requested_sub_products=$@
echo "List of requested products is:  $requested_sub_products"
# arg1 should contain the list of sub-products. Check that it isn't empty...
if [ -z "${requested_sub_products}" ] ; then
   echo "Usage: copy_bulk_image_to_s3.sh  <list of sub_products>"
   echo "Exiting."
   exit 1
fi

for i in $requested_sub_products
do
    if [[ "$valid_sub_products" !=  *" $i "* ]] ; then
        echo "Invalid product $i"
        echo "Valid sub_products are: $valid_sub_products"
        echo "Exiting."
        exit 1
    fi
done

for i in $requested_sub_products
do
    echo "Copy of sub-product $i begins at `date`"
    /export/home/dps/awscopy/copyDirectoryToS3.sh -b free.bulk-gateway.live.ch.gov.uk -d /export/home/new-bulk/image/customers/$i -a bulk-image/$i -z 10
    echo "Copy of sub-product $i  ends at `date`"
    echo "------------------------------------------------------------------------"
done

echo "****************************************************************************"
echo "$0 ends at `date`"
echo "****************************************************************************"

