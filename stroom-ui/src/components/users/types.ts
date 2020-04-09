export interface StoreState {
  changePasswordErrorMessage: any;
  errorStatus?: string;
  errorText?: string;
  password: string;
}

export interface User {
  email: string;
  enabled: boolean;
  inactive: boolean;
  locked: boolean;
  firstName: string;
  lastName: string;
  comments: string;
  password: string;
  forcePasswordChange: boolean;
  id?: string;
  lastLogin?: any;
  loginCount?: number;
  neverExpires?: boolean;
  createdByUser?: any;
  createdOn?: any;
  updatedByUser?: any;
  updatedOn?: any;
}
