/*

A work in progress. This will produce the "raster as contextual layer's" *.classes.json instead of using the built in process.

*/

#include <stdio.h>
#include <stdint.h>

#define BUFFER_SIZE 1024*1024  // Adjust this value according to your system's memory capacity

int main() {
    FILE *file = fopen("/data/spatial-data/layer/land_use2.bil", "rb");
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
    long numChunks = fileSize / (BUFFER_SIZE * sizeof(unsigned char));
    if (fileSize % (BUFFER_SIZE * sizeof(unsigned char)) != 0) {
        numChunks++;  // There is a partial chunk at the end of the file
    }

    // Buffer to hold each chunk
    unsigned char buffer[BUFFER_SIZE];

    int nrows = 73752;
    int ncols = 98262;

    // initialize an array of length 100 with 0's
    int hist[192*73752] = {0};
    int minx[192] = {0};
    int miny[192] = {0};
    int maxx[192] = {0};
    int maxy[192] = {0};

    printf("chunks %ld\n", numChunks);

    long pos = 0;


    // Process each chunk
    for (long i = 0; i < numChunks; i++) {
//        printf("%ld,", i);
//        printf(".");
        // Read the chunk into the buffer
        fread(buffer, sizeof(int16_t), BUFFER_SIZE, file);

        // Iterate over the buffer and add 1 to each value
        for (int j = 0; j < BUFFER_SIZE; j++) {
            int row = pos / ncols;
            int col = pos % ncols;

            hist[buffer[j]]++;

            minx[buffer[j]] = minx[buffer[j]] < col ? minx[buffer[j]] : col;
            miny[buffer[j]] = miny[buffer[j]] < row ? miny[buffer[j]] : row;
            maxx[buffer[j]] = maxx[buffer[j]] > col ? maxx[buffer[j]] : col;
            maxy[buffer[j]] = maxy[buffer[j]] > row ? maxy[buffer[j]] : row;

//            printf("%d,", buffer[j]);
//            buffer[j] = binarySearch(arr, 0, 191, buffer[j]);

            pos++;
        }

        // Write the modified data to the output file
        fwrite(buffer, sizeof(int16_t), BUFFER_SIZE, outputFile);
    }

    // print the histogram
//    for (int i = 0; i < 1000; i++) {
//        printf("\n%d: %d", i, hist[i]);
//    }

    for (int i = 0; i < 192; i++) {
        printf("\n%d: %d, %d, %d, %d", i, hist[i], minx[i], miny[i], maxx[i], maxy[i]);
    }

    fclose(file);
    fclose(outputFile);

    return 0;
}
