
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef FLOOR_UF_H
#define FLOOR_UF_H
; 
float floor_uf(float x){
    return floor(x);; 
}

#endif
 ; 
void lift_floor(float * v_initial_param_263_142, float * & v_user_func_265_143, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_265_143 = reinterpret_cast<float *>(malloc((v_N_0 * sizeof(float)))); 
    // For each element processed sequentially
    for (int v_i_141 = 0;(v_i_141 <= (-1 + v_N_0)); (++v_i_141)){
        v_user_func_265_143[v_i_141] = floor_uf(v_initial_param_263_142[v_i_141]); 
    }
}
}; 