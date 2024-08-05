/*
Exmaple for converting an int16 raster values so that it can be converted to int8, halving the file size.

1. set the .bil file to open.
2. set "arr", such that it is an ordered list of existing values. The new value will be the index position of the existing value.
3. set the output.bil file for writing.

*/

#include <stdio.h>
#include <stdint.h>

#define BUFFER_SIZE 1024*1024  // Adjust this value according to your system's memory capacity

// A recursive binary search function. It returns location of x in
// given array arr[l..r] is present, otherwise returns -1
int binarySearch(int arr[], int l, int r, int x){
    if (r >= l) {
        int mid = l + (r - l) / 2;

        // If the element is present at the middle itself
        if (arr[mid] == x)
            return mid;

        // If element is smaller than mid, then it can only be present
        // in left subarray
        if (arr[mid] > x)
            return binarySearch(arr, l, mid - 1, x);

        // Else the element can only be present in right subarray
        return binarySearch(arr, mid + 1, r, x);
    }

    // We reach here when element is not present in array
    return -1;
}

int main() {
    FILE *file = fopen("/data/spatial-data/uploads/land_use/land_use.bil", "rb");
    if (file == NULL) {
        printf("Failed to open file.\n");
        return 1;
    }

    FILE *outputFile = fopen("output.bil", "wb");
    if (outputFile == NULL) {
        printf("Failed to open output file.\n");
        return 1;
    }

    // Determine the size of the file
    fseek(file, 0, SEEK_END);
    long fileSize = ftell(file);
    fseek(file, 0, SEEK_SET);

    // Calculate the number of chunks
    long numChunks = fileSize / (BUFFER_SIZE * sizeof(int16_t));
    if (fileSize % (BUFFER_SIZE * sizeof(int16_t)) != 0) {
        numChunks++;  // There is a partial chunk at the end of the file
    }

    // Buffer to hold each chunk
    int16_t buffer[BUFFER_SIZE];

    // create an int array to hold [1, 2, 3]
    int arr[192] = {0,110,111,112,113,114,115,116,117,120,121,122,123,124,125,130,131,132,133,134,210,220,221,222,310,311,312,313,314,320,321,322,323,324,325,330,331,332,333,334,335,336,337,338,340,341,342,343,344,345,346,347,348,349,350,351,352,353,360,361,362,363,364,365,410,411,412,413,414,420,421,422,423,424,430,431,432,433,434,435,436,437,438,439,440,441,442,443,444,445,446,447,448,449,450,451,452,453,454,460,461,462,463,464,465,510,511,512,513,514,515,520,521,522,523,524,525,526,527,528,530,531,532,533,534,535,536,537,538,540,541,542,543,544,545,550,551,552,553,554,555,560,561,562,563,564,565,566,567,570,571,572,573,574,575,580,581,582,583,584,590,591,592,593,594,595,610,611,612,613,614,620,621,622,623,630,631,632,633,640,641,642,643,650,651,652,653,654,660,661,662,663};

    // initialize an array of length 100 with 0's
    int hist[1000] = {0};

    printf("chunks %ld\n", numChunks);

    // Process each chunk
    for (long i = 0; i < numChunks; i++) {
//        printf("%ld,", i);
//        printf(".");
        // Read the chunk into the buffer
        fread(buffer, sizeof(int16_t), BUFFER_SIZE, file);

        // Iterate over the buffer and add 1 to each value
        for (int j = 0; j < BUFFER_SIZE; j++) {
            hist[buffer[j]]++;
//            printf("%d,", buffer[j]);
            buffer[j] = binarySearch(arr, 0, 191, buffer[j]);
        }

        // Write the modified data to the output file
        fwrite(buffer, sizeof(int16_t), BUFFER_SIZE, outputFile);
    }

    // print the histogram
    for (int i = 0; i < 1000; i++) {
        printf("\n%d: %d", i, hist[i]);
    }

    fclose(file);
    fclose(outputFile);

    return 0;
}
