
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef TUPLE2_FLOAT_FLOAT_H
#define TUPLE2_FLOAT_FLOAT_H
; 
// NOTE: trying to print unprintable type: float
 // NOTE: trying to print unprintable type: float
typedef struct{
    float _0;
    float _1;
} Tuple2_float_float;


#endif
 ; 
#ifndef MODF_UF_H
#define MODF_UF_H
; 
Tuple2_float_float modf_uf(float x){
    if(x>=0) return { x - floor(x), floor(x) }; else return  { x - round(x), round(x)} ;; 
}

#endif
 ; 
void modf(float * v_initial_param_754_348, Tuple2_float_float * & v_user_func_756_349, int v_N_0){
    // Allocate memory for output pointers
    v_user_func_756_349 = reinterpret_cast<Tuple2_float_float *>(malloc((v_N_0 * sizeof(Tuple2_float_float)))); 
    // For each element processed sequentially
    for (int v_i_347 = 0;(v_i_347 <= (-1 + v_N_0)); (++v_i_347)){
        v_user_func_756_349[v_i_347] = modf_uf(v_initial_param_754_348[v_i_347]); 
    }
}
}; 