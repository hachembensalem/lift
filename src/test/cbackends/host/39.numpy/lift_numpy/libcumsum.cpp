
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef ADD_H
#define ADD_H
; 
float add(float l, float r){
    { return (l + r); }; 
}

#endif
 ; 
void cumsum(float * v_initial_param_293_137, float * & v_user_func_296_138, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_296_138 = reinterpret_cast<float *>(malloc((v_N_0 * sizeof(float)))); 
    // For each element scanned sequentially
    float scan_acc_303 = 0.0f;
    for (int v_i_136 = 0;(v_i_136 <= (-1 + v_N_0)); (++v_i_136)){
        scan_acc_303 = add(scan_acc_303, v_initial_param_293_137[v_i_136]); 
        v_user_func_296_138[v_i_136] = scan_acc_303; 
    }
}
}; 