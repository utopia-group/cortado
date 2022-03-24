"""
Take union of CSVs, leaving entries blank when one CSV has a column that another does not share
"""
import argparse
import csv
import os

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Merge any number CSV files.')
    parser.add_argument('csvFileName', type=str, nargs='+', help='csv files to merge')
    parser.add_argument('--out', '-o', type=str, nargs=1, help='output file name', default="out.csv")

    args = vars(parser.parse_args())

    print(args)
    input_file_names = args["csvFileName"]
    output_file_name, = args["out"]

    if os.path.exists(output_file_name) and output_file_name not in input_file_names:
        raise ValueError(f"Attempting to overwrite file {output_file_name}. Only input files can be overwritten")

    print("Merging " + ', '.join(input_file_names) + " into " + output_file_name)

    all_rows = []
    keys = set()
    for input_file_name in input_file_names:
        with open(input_file_name) as csv_file:
            reader = csv.DictReader(csv_file)
            all_rows += [row for row in reader]
            keys.update(reader.fieldnames)

    with open(output_file_name, 'w') as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=sorted(keys))
        writer.writeheader()
        for row in all_rows:
            writer.writerow(row)
