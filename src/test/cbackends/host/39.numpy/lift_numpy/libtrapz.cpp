
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef TRAPZ_H
#define TRAPZ_H
; 
float trapz(float x1, float x2, float y1, float y2){
    { return (x2-x1)*(y2+y1)/2.0f; }; 
}

#endif
 ; 
#ifndef ADD_H
#define ADD_H
; 
float add(float l, float r){
    { return (l + r); }; 
}

#endif
 ; 
void trapz(float * v_initial_param_402_192, float * v_initial_param_403_193, float * & v_user_func_406_196, int v_N_0){
    // Allocate memory for output pointers
    float * v_user_func_430_195 = reinterpret_cast<float *>(malloc(((-1 + v_N_0) * sizeof(float))));
    v_user_func_406_196 = reinterpret_cast<float *>(malloc((1 * sizeof(float)))); 
    // For each element processed sequentially
    for (int v_i_191 = 0;(v_i_191 <= (-2 + v_N_0)); (++v_i_191)){
        v_user_func_430_195[v_i_191] = trapz(v_initial_param_402_192[v_i_191], v_initial_param_402_192[(1 + v_i_191)], v_initial_param_403_193[v_i_191], v_initial_param_403_193[(1 + v_i_191)]); 
    }
    // For each element reduced sequentially
    v_user_func_406_196[0] = 0.0f; 
    for (int v_i_190 = 0;(v_i_190 <= (-2 + v_N_0)); (++v_i_190)){
        v_user_func_406_196[0] = add(v_user_func_406_196[0], v_user_func_430_195[v_i_190]); 
    }
}
}; 