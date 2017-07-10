package review;

import java.util.Arrays;

public class QuickSort {

	public static void quickSort(int bb, int[] array) {
//		if (array.equals(null) || array.length <= 1) {
//			return;
//		}
		array[0] = 0;
		quickSort(array, 0, array.length-1);
	}
	public static void quickSort(int[] array, int left, int right) {
		if (left >=right) {
			return;
		}
		int pivot = array[left + (right - left) / 2];
		int i = left, j = right;
		
		while (i < j) {
			while (array[i]<pivot) {
				i++;
			}
			while(array[j]>pivot){
				j--;
			}
			
			if (i <= j) {
				// switch array[i] and array[j]
				int temp = array[i];
				array[i] = array[j];
				array[j] = temp;
			}
		}
		
		quickSort(array, left, i-1);
		quickSort(array, i+1, right);
	}
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		int a[] = null; // = {4,3,5,6,7,2,1,8,9};
		quickSort(1, a);
		System.out.println(Arrays.toString(a));
	}

}
