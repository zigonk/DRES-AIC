/**
 * DRES API
 * API for DRES (Distributed Retrieval Evaluation Server), Version 1.0
 *
 * The version of the OpenAPI document: 1.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


export interface Judgement { 
    id: number;
    judgement: Judgement.JudgementEnum;
}
export namespace Judgement {
    export type JudgementEnum = 'CORRECT' | 'WRONG' | 'INDETERMINATE' | 'UNDECIDABLE';
    export const JudgementEnum = {
        CORRECT: 'CORRECT' as JudgementEnum,
        WRONG: 'WRONG' as JudgementEnum,
        INDETERMINATE: 'INDETERMINATE' as JudgementEnum,
        UNDECIDABLE: 'UNDECIDABLE' as JudgementEnum
    };
}


