
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef PROD2_UF_H
#define PROD2_UF_H
; 
float prod2_uf(float l, float r){
    { return (l * r); }; 
}

#endif
 ; 
void nancumprod(float * v_initial_param_306_167, float * & v_user_func_309_168, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_309_168 = reinterpret_cast<float *>(malloc((v_N_0 * sizeof(float)))); 
    // For each element scanned sequentially
    float scan_acc_316 = 1.0f;
    for (int v_i_166 = 0;(v_i_166 <= (-1 + v_N_0)); (++v_i_166)){
        scan_acc_316 = prod2_uf(scan_acc_316, v_initial_param_306_167[v_i_166]); 
        v_user_func_309_168[v_i_166] = scan_acc_316; 
    }
}
}; 